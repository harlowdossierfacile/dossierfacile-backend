package fr.dossierfacile.garbagecollector.transactions;

import fr.dossierfacile.garbagecollector.model.object.Object;
import fr.dossierfacile.garbagecollector.repo.file.FileRepository;
import fr.dossierfacile.garbagecollector.repo.object.ObjectRepository;
import fr.dossierfacile.garbagecollector.transactions.interfaces.ObjectTransactions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ObjectTransactionsImpl implements ObjectTransactions {
    private final ObjectRepository objectRepository;
    private final FileRepository fileRepository;

    @Override
    @Transactional
    public void saveObjectIfDoesntExist(String nameFile) {
        if (objectRepository.findObjectByPath(nameFile) == null) {
            Object object = new Object();
            // If the [File] doesn't exist in the [Database] then it will be marked with [true] to delete
            object.setToDelete(!fileRepository.existsObject(nameFile));
            object.setPath(nameFile);
            objectRepository.save(object);
        }
    }

    @Override
    @Transactional
    public void deleteAllObjects() {
        objectRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteListObjects(List<Object> objectList) {
        objectRepository.deleteAll(objectList);
    }

    @Override
    @Transactional
    public void deleteObjectsMayorThan(String path) {
        objectRepository.deleteObjectsMayorThan(path);
    }
}
